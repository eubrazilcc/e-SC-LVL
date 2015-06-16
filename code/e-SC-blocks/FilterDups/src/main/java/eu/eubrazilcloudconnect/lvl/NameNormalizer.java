/**
 * e-Science Central Copyright (C) 2008-2015 School of Computing Science,
 * Newcastle University
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 2 as published by the
 * Free Software Foundation at: http://www.gnu.org/licenses/gpl-2.0.html
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, 5th Floor, Boston, MA 02110-1301, USA.
 */
package eu.eubrazilcloudconnect.lvl;

import org.pipeline.core.data.DataException;

import java.util.HashSet;


interface NameNormalizer
{
    String normalize(String sequenceName) throws DataException;

    void setCollidingNames(HashSet<String> collidingNames);

    void clear();
}
